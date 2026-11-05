import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class UpdateTemplate {
    public static void main(String[] args) throws Exception {
        String filePath = "src/main/resources/templates/adminProducts.html";
        String content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);

        // 1. Artisan Image
        content = content.replace("<img \r\n                                    src=\"https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=50&h=50&fit=crop&crop=face\" \r\n                                    alt=\"Elena Rodriguez\" \r\n                                    class=\"w-12 h-12 rounded-full object-cover\"\r\n                                />",
                                  "<img id=\"modalArtisanImage\"\r\n                                    src=\"https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=50&h=50&fit=crop&crop=face\" \r\n                                    alt=\"Artisan Image\" \r\n                                    class=\"w-12 h-12 rounded-full object-cover\"\r\n                                />");

        // 2. Artisan Name & Shop
        content = content.replace("<p class=\"font-semibold text-[#5c4a3d]\">Elena Rodriguez</p>\r\n                                    <p class=\"text-sm text-[#8b7355]\">Ceramics Artist</p>",
                                  "<p id=\"modalArtisanName\" class=\"font-semibold text-[#5c4a3d]\">Elena Rodriguez</p>\r\n                                    <p id=\"modalArtisanShop\" class=\"text-sm text-[#8b7355]\">Ceramics Artist</p>");

        // 3. Artisan Rating & Products
        content = content.replace("<span><i class=\"fas fa-star text-amber-500 mr-1\"></i>4.9 (127 reviews)</span>\r\n                                <span><i class=\"fas fa-box mr-1\"></i>43 products</span>",
                                  "<span><i class=\"fas fa-star text-amber-500 mr-1\"></i><span id=\"modalArtisanRating\">4.9 (127 reviews)</span></span>\r\n                                <span><i class=\"fas fa-box mr-1\"></i><span id=\"modalArtisanProducts\">43 products</span></span>");

        // 4. Customer Reviews Count & Container
        content = content.replace("Customer Reviews (8)\r\n                            </h4>\r\n                            <div class=\"space-y-3 max-h-64 overflow-y-auto\">",
                                  "Customer Reviews (<span id=\"modalReviewsCount\">8</span>)\r\n                            </h4>\r\n                            <div id=\"modalReviewsContainer\" class=\"space-y-3 max-h-64 overflow-y-auto\">");

        // 5. Admin Actions Buttons Grid
        String oldButtons = "<div class=\"grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4\">\r\n" +
                "                        <button onclick=\"handleAdminAction('approve')\" class=\"bg-green-600 hover:bg-green-700 text-white font-medium py-3 rounded-lg transition-colors flex items-center justify-center gap-2\">\r\n" +
                "                            <i class=\"fas fa-check-circle\"></i>\r\n" +
                "                            Approve Listing\r\n" +
                "                        </button>\r\n" +
                "                        <button onclick=\"handleAdminAction('remove')\" class=\"bg-red-600 hover:bg-red-700 text-white font-medium py-3 rounded-lg transition-colors flex items-center justify-center gap-2\">\r\n" +
                "                            <i class=\"fas fa-trash\"></i>\r\n" +
                "                            Remove Listing\r\n" +
                "                        </button>\r\n" +
                "                        <button onclick=\"handleAdminAction('flag')\" class=\"bg-amber-500 hover:bg-amber-600 text-white font-medium py-3 rounded-lg transition-colors flex items-center justify-center gap-2\">\r\n" +
                "                            <i class=\"fas fa-flag\"></i>\r\n" +
                "                            Flag for Review\r\n" +
                "                        </button>\r\n" +
                "                        <button onclick=\"handleAdminAction('contact')\" class=\"bg-[#2563eb] hover:bg-[#1d4ed8] text-white font-medium py-3 rounded-lg transition-colors flex items-center justify-center gap-2\">\r\n" +
                "                            <i class=\"fas fa-envelope\"></i>\r\n" +
                "                            Contact Artisan\r\n" +
                "                        </button>\r\n" +
                "                    </div>";
        String newButtons = "<div class=\"grid grid-cols-1 md:grid-cols-2 gap-4\">\r\n" +
                "                        <button id=\"modalApproveBtn\" class=\"bg-green-600 hover:bg-green-700 text-white font-medium py-3 rounded-lg transition-colors flex items-center justify-center gap-2\">\r\n" +
                "                            <i class=\"fas fa-check-circle\"></i>\r\n" +
                "                            Approve Listing\r\n" +
                "                        </button>\r\n" +
                "                        <button id=\"modalRemoveBtn\" class=\"bg-red-600 hover:bg-red-700 text-white font-medium py-3 rounded-lg transition-colors flex items-center justify-center gap-2\">\r\n" +
                "                            <i class=\"fas fa-trash\"></i>\r\n" +
                "                            Remove Listing\r\n" +
                "                        </button>\r\n" +
                "                    </div>";
        content = content.replace(oldButtons, newButtons);

        // 6. Handle Admin Action JS
        String oldHandleAction = "        function handleAdminAction(action) {\r\n" +
                "            let message = '';\r\n" +
                "            let type = 'info';\r\n" +
                "\r\n" +
                "            if (action === 'approve') {\r\n" +
                "                message = 'Product listing approved successfully.';\r\n" +
                "                type = 'success';\r\n" +
                "            } else if (action === 'remove') {\r\n" +
                "                message = 'Product listing removed. Artisan has been notified.';\r\n" +
                "                type = 'info';\r\n" +
                "            } else if (action === 'flag') {\r\n" +
                "                message = 'Product flagged for review.';\r\n" +
                "                type = 'warning';\r\n" +
                "            } else if (action === 'contact') {\r\n" +
                "                message = 'Opening contact form with artisan...';\r\n" +
                "                type = 'info';\r\n" +
                "            }\r\n" +
                "\r\n" +
                "            showNotification(message, type);\r\n" +
                "\r\n" +
                "            if (action !== 'contact') {\r\n" +
                "                setTimeout(() => {\r\n" +
                "                    closeProductModal();\r\n" +
                "                }, 1000);\r\n" +
                "            }\r\n" +
                "        }";
        String newHandleAction = "        function handleAdminAction(action, productId) {\r\n" +
                "            let status = (action === 'approve') ? 'active' : 'hidden';\r\n" +
                "            fetch(`/api/products/${productId}/status?status=${status}`, {\r\n" +
                "                method: 'PUT'\r\n" +
                "            })\r\n" +
                "            .then(res => res.json())\r\n" +
                "            .then(data => {\r\n" +
                "                if (data.success) {\r\n" +
                "                    showNotification(action === 'approve' ? 'Product approved and visible!' : 'Product listing removed (hidden)!', 'success');\r\n" +
                "                    setTimeout(() => {\r\n" +
                "                        window.location.reload();\r\n" +
                "                    }, 1000);\r\n" +
                "                } else {\r\n" +
                "                    showNotification(data.message || 'Action failed.', 'error');\r\n" +
                "                }\r\n" +
                "            })\r\n" +
                "            .catch(err => {\r\n" +
                "                console.error(err);\r\n" +
                "                showNotification('An error occurred.', 'error');\r\n" +
                "            });\r\n" +
                "        }";
        content = content.replace(oldHandleAction, newHandleAction);

        // 7. Open Product Modal JS - using regex
        content = content.replaceAll("(?s)function openProductModal\\(productId\\) \\{.*?\\} \\.catch\\(err => \\{.*?\\}\\);\\s*\\}",
            "function openProductModal(productId) {\r\n" +
            "            fetch(`/api/products/${productId}`)\r\n" +
            "                .then(res => res.json())\r\n" +
            "                .then(data => {\r\n" +
            "                    if (data.success && data.product) {\r\n" +
            "                        const p = data.product;\r\n" +
            "                        \r\n" +
            "                        document.querySelector('#productModal h3').textContent = p.title;\r\n" +
            "                        document.querySelector('#productModal .text-sm.text-\\\\[\\\\#8b7355\\\\]').textContent = 'SKU: PRD-' + p.id;\r\n" +
            "                        document.querySelector('#productModal .text-\\\\[\\\\#5c4a3d\\\\]').textContent = p.description || 'No description provided.';\r\n" +
            "                        \r\n" +
            "                        document.getElementById('modalPrice').textContent = 'BD ' + p.price.toFixed(3);\r\n" +
            "                        document.getElementById('modalDate').textContent = p.addingDate || 'N/A';\r\n" +
            "                        \r\n" +
            "                        const statusBadge = document.querySelector('#productModal .status-badge');\r\n" +
            "                        const isOutOfStock = p.stockQuantity <= 0 || p.status === 'out of stock';\r\n" +
            "                        if (p.status === 'hidden') {\r\n" +
            "                            statusBadge.className = 'status-badge bg-gray-100 text-gray-700';\r\n" +
            "                            statusBadge.innerHTML = '<i class=\"fas fa-eye-slash\"></i> Hidden / Removed';\r\n" +
            "                        } else if (isOutOfStock) {\r\n" +
            "                            statusBadge.className = 'status-badge bg-red-100 text-red-700';\r\n" +
            "                            statusBadge.innerHTML = '<i class=\"fas fa-times-circle\"></i> Out of Stock';\r\n" +
            "                        } else {\r\n" +
            "                            statusBadge.className = 'status-badge bg-green-100 text-green-700';\r\n" +
            "                            statusBadge.innerHTML = '<i class=\"fas fa-check-circle\"></i> Active';\r\n" +
            "                        }\r\n" +
            "                        \r\n" +
            "                        const mainImage = document.getElementById('mainImage');\r\n" +
            "                        mainImage.src = p.imageUrl || 'https://images.unsplash.com/photo-1610701596007-11502861dcfa?w=600&h=600&fit=crop';\r\n" +
            "                        \r\n" +
            "                        const gallery = document.getElementById('thumbnailGallery');\r\n" +
            "                        gallery.innerHTML = `\r\n" +
            "                            <img \r\n" +
            "                                src=\"${p.imageUrl || 'https://images.unsplash.com/photo-1610701596007-11502861dcfa?w=150&h=150&fit=crop'}\" \r\n" +
            "                                class=\"gallery-thumbnail active w-20 h-20 object-cover rounded-lg cursor-pointer border-2 border-[#c17c5f]\"\r\n" +
            "                                onclick=\"changeMainImage(this)\"\r\n" +
            "                            />\r\n" +
            "                        `;\r\n" +
            "                        \r\n" +
            "                        document.getElementById('modalArtisanName').textContent = p.artisanName || 'Unknown Artisan';\r\n" +
            "                        \r\n" +
            "                        const artisanImg = document.getElementById('modalArtisanImage');\r\n" +
            "                        if (p.artisan && p.artisan.profilePicture) {\r\n" +
            "                            artisanImg.src = p.artisan.profilePicture;\r\n" +
            "                        } else {\r\n" +
            "                            artisanImg.src = 'https://cdn-icons-png.flaticon.com/512/149/149071.png';\r\n" +
            "                        }\r\n" +
            "\r\n" +
            "                        document.getElementById('modalArtisanShop').textContent = (p.artisan && p.artisan.shopName) ? p.artisan.shopName : 'Artisan Shop';\r\n" +
            "\r\n" +
            "                        document.getElementById('modalArtisanProducts').textContent = `${data.artisanTotalProducts} products`;\r\n" +
            "                        document.getElementById('modalArtisanRating').textContent = `${data.artisanAverageRating.toFixed(1)} (${data.artisanReviewsCount} reviews)`;\r\n" +
            "\r\n" +
            "                        document.getElementById('modalReviewsCount').textContent = data.reviews.length;\r\n" +
            "                        const reviewsContainer = document.getElementById('modalReviewsContainer');\r\n" +
            "                        if (data.reviews.length === 0) {\r\n" +
            "                            reviewsContainer.innerHTML = `\r\n" +
            "                                <div class=\"text-center py-6 text-sm text-[#8b7355]\">\r\n" +
            "                                    No reviews yet for this product.\r\n" +
            "                                </div>\r\n" +
            "                            `;\r\n" +
            "                        } else {\r\n" +
            "                            reviewsContainer.innerHTML = data.reviews.map(r => {\r\n" +
            "                                let starHtml = '';\r\n" +
            "                                for (let i = 1; i <= 5; i++) {\r\n" +
            "                                    if (i <= r.rating) {\r\n" +
            "                                        starHtml += '<i class=\"fas fa-star text-amber-500 text-xs\"></i>';\r\n" +
            "                                    } else {\r\n" +
            "                                        starHtml += '<i class=\"far fa-star text-amber-500 text-xs\"></i>';\r\n" +
            "                                    }\r\n" +
            "                                }\r\n" +
            "                                return `\r\n" +
            "                                    <div class=\"bg-gray-50 rounded-lg p-3\">\r\n" +
            "                                        <div class=\"flex items-center justify-between mb-2\">\r\n" +
            "                                            <div class=\"flex items-center gap-2\">\r\n" +
            "                                                <img \r\n" +
            "                                                    src=\"${r.reviewerImage}\" \r\n" +
            "                                                    alt=\"${r.reviewerName}\" \r\n" +
            "                                                    class=\"w-8 h-8 rounded-full object-cover\"\r\n" +
            "                                                />\r\n" +
            "                                                <span class=\"text-sm font-medium text-[#5c4a3d]\">${r.reviewerName}</span>\r\n" +
            "                                            </div>\r\n" +
            "                                            <div class=\"flex items-center gap-1\">\r\n" +
            "                                                ${starHtml}\r\n" +
            "                                            </div>\r\n" +
            "                                        </div>\r\n" +
            "                                        <p class=\"text-sm text-[#5c4a3d]\">${r.comment}</p>\r\n" +
            "                                        <p class=\"text-xs text-[#8b7355] mt-1\">${r.date}</p>\r\n" +
            "                                    </div>\r\n" +
            "                                `;\r\n" +
            "                            }).join('');\r\n" +
            "                        }\r\n" +
            "\r\n" +
            "                        document.getElementById('modalApproveBtn').onclick = () => handleAdminAction('approve', p.id);\r\n" +
            "                        document.getElementById('modalRemoveBtn').onclick = () => handleAdminAction('remove', p.id);\r\n" +
            "\r\n" +
            "                        const modal = document.getElementById('productModal');\r\n" +
            "                        modal.classList.remove('hidden');\r\n" +
            "                        modal.classList.add('flex');\r\n" +
            "                        document.body.style.overflow = 'hidden';\r\n" +
            "                    } else {\r\n" +
            "                        alert('Failed to load product details.');\r\n" +
            "                    }\r\n" +
            "                })\r\n" +
            "                .catch(err => {\r\n" +
            "                    console.error(err);\r\n" +
            "                    alert('Error loading product details.');\r\n" +
            "                });\r\n" +
            "        }");

        Files.write(Paths.get(filePath), content.getBytes(StandardCharsets.UTF_8));
        System.out.println("Update Template execution successful!");
    }
}
